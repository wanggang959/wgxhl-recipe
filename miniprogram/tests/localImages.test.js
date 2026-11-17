const test=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path')

test('built-in avatars and login illustrations are packaged as PNG files',()=>{
  const root=path.join(__dirname,'..','assets')
  const files=[...Array.from({length:15},(_,i)=>`avatars/${String(i+1).padStart(2,'0')}.png`),...['login-bg','login-chef','login-pot','login-family'].map(name=>`login/${name}.png`)]
  for(const file of files){
    const bytes=fs.readFileSync(path.join(root,file))
    assert.deepEqual([...bytes.subarray(0,8)],[137,80,78,71,13,10,26,10],file)
    assert.ok(bytes.length<200*1024,`${file} exceeds 200 KiB`)
  }
})

test('old built-in WebP files are excluded without excluding other images',()=>{
  const config=JSON.parse(fs.readFileSync(path.join(__dirname,'..','project.config.json'),'utf8'))
  const rule=config.packOptions.ignore.find(item=>item.type==='regexp' && item.value.includes('avatars|login'))
  assert.ok(rule)
  const ignored=new RegExp(rule.value)
  assert.ok(ignored.test('assets/avatars/01.webp'))
  assert.ok(ignored.test('assets/login/login-bg.webp'))
  assert.equal(ignored.test('assets/avatars/01.png'),false)
  assert.equal(ignored.test('assets/other/cover.webp'),false)
})
